package com.tomasps.virga.feature.d1.sql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SqlSafetyTest {

    @Test
    fun `plain select is not risky`() {
        assertTrue(SqlSafety.risks("SELECT * FROM orders LIMIT 10").isEmpty())
    }

    @Test
    fun `drop table is flagged as DROP`() {
        // Act
        val risks = SqlSafety.risks("DROP TABLE orders")

        // Assert
        assertEquals(listOf(RiskKind.DROP), risks.single().kinds.toList())
    }

    @Test
    fun `alter table is flagged as ALTER`() {
        val risks = SqlSafety.risks("ALTER TABLE orders ADD COLUMN note TEXT")
        assertEquals(setOf(RiskKind.ALTER), risks.single().kinds)
    }

    @Test
    fun `delete without where is flagged, delete with where is not`() {
        assertEquals(setOf(RiskKind.DELETE_WITHOUT_WHERE), SqlSafety.risks("DELETE FROM orders").single().kinds)
        assertTrue(SqlSafety.risks("DELETE FROM orders WHERE id = 3").isEmpty())
    }

    @Test
    fun `update without where is flagged, update with where is not`() {
        assertEquals(setOf(RiskKind.UPDATE_WITHOUT_WHERE), SqlSafety.risks("update orders set total = 0").single().kinds)
        assertTrue(SqlSafety.risks("UPDATE orders SET total = 0 WHERE id = 3").isEmpty())
    }

    @Test
    fun `keywords inside string literals and comments are ignored`() {
        // Arrange
        val sql = "SELECT 'DROP TABLE x' AS note -- DELETE FROM orders\n/* ALTER */ FROM t"

        // Act / Assert
        assertTrue(SqlSafety.risks(sql).isEmpty())
    }

    @Test
    fun `each risky statement in a batch is reported separately`() {
        // Arrange
        val sql = "SELECT 1; DELETE FROM orders; UPDATE orders SET a = 1; DROP TABLE t"

        // Act
        val risks = SqlSafety.risks(sql)

        // Assert
        assertEquals(3, risks.size)
        assertEquals("DELETE FROM orders", risks[0].statement)
        assertEquals(setOf(RiskKind.DROP), risks[2].kinds)
    }

    @Test
    fun `a quoted identifier that spells a keyword is not a keyword`() {
        assertTrue(SqlSafety.risks("SELECT \"DROP\" FROM t").isEmpty())
    }

    @Test
    fun `a WHERE inside an update subquery does not key the outer update`() {
        // The outer UPDATE has no WHERE, so every row changes and the user must confirm.
        val sql = "UPDATE orders SET total = (SELECT max(x) FROM u WHERE id = 1)"

        assertEquals(setOf(RiskKind.UPDATE_WITHOUT_WHERE), SqlSafety.risks(sql).single().kinds)
    }

    @Test
    fun `a WHERE inside a CTE body does not key the outer delete`() {
        val sql = "WITH x AS (SELECT 1 WHERE 1) DELETE FROM orders"

        assertEquals(setOf(RiskKind.DELETE_WITHOUT_WHERE), SqlSafety.risks(sql).single().kinds)
    }

    @Test
    fun `a keyed delete after a CTE is not flagged`() {
        assertTrue(SqlSafety.risks("WITH x AS (SELECT 1) DELETE FROM orders WHERE id IN (SELECT id FROM x)").isEmpty())
    }

    @Test
    fun `a keyed update with a subquery in its SET clause is not flagged`() {
        assertTrue(SqlSafety.risks("UPDATE orders SET total = (SELECT max(x) FROM u) WHERE id = 3").isEmpty())
    }

    @Test
    fun `only reads are considered read-only when deciding how a failure is worded`() {
        assertFalse(SqlSafety.mayWrite("SELECT 1; PRAGMA table_info(t)"))
        assertTrue(SqlSafety.mayWrite("SELECT 1; INSERT INTO t VALUES (1)"))
        assertTrue(SqlSafety.mayWrite("WITH x AS (SELECT 1) DELETE FROM t WHERE id = 1"))
    }

    @Test
    fun `a double dash inside a string literal does not hide a later WHERE from the check`() {
        // The literal runs to the end of the line; the WHERE after it is text, so every row changes.
        val sql = "UPDATE t SET a = '--x',\n b = ' WHERE '"

        assertEquals(setOf(RiskKind.UPDATE_WITHOUT_WHERE), SqlSafety.risks(sql).single().kinds)
    }

    @Test
    fun `a block comment opener inside a string literal does not hide a WHERE in the literal`() {
        val sql = "UPDATE t SET a = '/*' || 'x*/ WHERE 1'"

        assertEquals(setOf(RiskKind.UPDATE_WITHOUT_WHERE), SqlSafety.risks(sql).single().kinds)
    }

    @Test
    fun `a WHERE that only sits inside a comment does not key the statement`() {
        assertEquals(setOf(RiskKind.DELETE_WITHOUT_WHERE), SqlSafety.risks("DELETE FROM t /* WHERE id = 1 */").single().kinds)
        assertEquals(setOf(RiskKind.DELETE_WITHOUT_WHERE), SqlSafety.risks("DELETE FROM t -- WHERE id = 1").single().kinds)
    }

    @Test
    fun `comments before the keyword do not hide a destructive statement`() {
        assertEquals(setOf(RiskKind.DROP), SqlSafety.risks("/* maintenance */ DROP -- now\nTABLE t").single().kinds)
        assertEquals(setOf(RiskKind.DROP), SqlSafety.risks("DROP/**/TABLE t").single().kinds)
    }

    @Test
    fun `odd casing and spacing are still recognised`() {
        assertEquals(setOf(RiskKind.DROP), SqlSafety.risks("dRoP   tAbLe t").single().kinds)
        assertEquals(setOf(RiskKind.DELETE_WITHOUT_WHERE), SqlSafety.risks("Delete\tFrom t").single().kinds)
        assertEquals(setOf(RiskKind.UPDATE_WITHOUT_WHERE), SqlSafety.risks("uPdAtE t sEt a = 1").single().kinds)
        assertTrue(SqlSafety.risks("delete from t wHeRe id = 1").isEmpty())
    }

    @Test
    fun `a keyword inside a bracketed or backtick identifier is not a keyword`() {
        assertTrue(SqlSafety.risks("SELECT [DROP], `DELETE` FROM t").isEmpty())
    }

    @Test
    fun `an escaped quote inside a literal keeps the literal open`() {
        // The literal is 'it''s -- WHERE x'; the trailing WHERE is inside it, so the update has no real WHERE.
        val sql = "UPDATE t SET a = 'it''s -- WHERE x'"

        assertEquals(setOf(RiskKind.UPDATE_WITHOUT_WHERE), SqlSafety.risks(sql).single().kinds)
    }
}

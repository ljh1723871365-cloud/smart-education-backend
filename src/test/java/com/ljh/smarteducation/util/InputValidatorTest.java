package com.ljh.smarteducation.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InputValidator 工具类的单元测试
 */
@DisplayName("InputValidator 工具类测试")
class InputValidatorTest {

    // ==================== 测试 isValidUsername() ====================

    @Test
    @DisplayName("测试有效用户名 - 应该返回 true")
    void testValidUsername_Success() {
        assertTrue(InputValidator.isValidUsername("user123"));
        assertTrue(InputValidator.isValidUsername("test_user"));
        assertTrue(InputValidator.isValidUsername("abc"));
        assertTrue(InputValidator.isValidUsername("User_Name_123"));
    }

    @Test
    @DisplayName("测试 null 用户名 - 应该返回 false")
    void testValidUsername_Null() {
        assertFalse(InputValidator.isValidUsername(null));
    }

    @Test
    @DisplayName("测试过短用户名 - 应该返回 false")
    void testValidUsername_TooShort() {
        assertFalse(InputValidator.isValidUsername("ab"));
        assertFalse(InputValidator.isValidUsername("a"));
        assertFalse(InputValidator.isValidUsername(""));
    }

    @Test
    @DisplayName("测试过长用户名 - 应该返回 false")
    void testValidUsername_TooLong() {
        String longUsername = "a".repeat(51);
        assertFalse(InputValidator.isValidUsername(longUsername));
    }

    @Test
    @DisplayName("测试包含非法字符的用户名 - 应该返回 false")
    void testValidUsername_InvalidChars() {
        assertFalse(InputValidator.isValidUsername("user@123"));
        assertFalse(InputValidator.isValidUsername("user 123"));
        assertFalse(InputValidator.isValidUsername("user-name"));
        assertFalse(InputValidator.isValidUsername("user.name"));
        assertFalse(InputValidator.isValidUsername("用户名"));
    }

    // ==================== 测试 isValidPassword() ====================

    @Test
    @DisplayName("测试有效密码 - 应该返回 true")
    void testValidPassword_Success() {
        assertTrue(InputValidator.isValidPassword("password123"));
        assertTrue(InputValidator.isValidPassword("abc123"));
        assertTrue(InputValidator.isValidPassword("123456"));
        assertTrue(InputValidator.isValidPassword("P@ssw0rd!"));
    }

    @Test
    @DisplayName("测试 null 密码 - 应该返回 false")
    void testValidPassword_Null() {
        assertFalse(InputValidator.isValidPassword(null));
    }

    @Test
    @DisplayName("测试过短密码 - 应该返回 false")
    void testValidPassword_TooShort() {
        assertFalse(InputValidator.isValidPassword("12345"));
        assertFalse(InputValidator.isValidPassword("abc"));
        assertFalse(InputValidator.isValidPassword(""));
    }

    @Test
    @DisplayName("测试过长密码 - 应该返回 false")
    void testValidPassword_TooLong() {
        String longPassword = "a".repeat(101);
        assertFalse(InputValidator.isValidPassword(longPassword));
    }

    // ==================== 测试 isValidTitle() ====================

    @Test
    @DisplayName("测试有效标题 - 应该返回 true")
    void testValidTitle_Success() {
        assertTrue(InputValidator.isValidTitle("测试标题"));
        assertTrue(InputValidator.isValidTitle("Test Title 123"));
        assertTrue(InputValidator.isValidTitle("标题-测试_123"));
        assertTrue(InputValidator.isValidTitle("Title, Test."));
    }

    @Test
    @DisplayName("测试 null 标题 - 应该返回 false")
    void testValidTitle_Null() {
        assertFalse(InputValidator.isValidTitle(null));
    }

    @Test
    @DisplayName("测试过长标题 - 应该返回 false")
    void testValidTitle_TooLong() {
        String longTitle = "标".repeat(201);
        assertFalse(InputValidator.isValidTitle(longTitle));
    }

    @Test
    @DisplayName("测试空标题 - 应该返回 false")
    void testValidTitle_Empty() {
        assertFalse(InputValidator.isValidTitle(""));
    }

    // ==================== 测试 containsSqlInjection() ====================

    @Test
    @DisplayName("测试正常输入 - 应该返回 false（无 SQL 注入）")
    void testContainsSqlInjection_NormalInput() {
        assertFalse(InputValidator.containsSqlInjection("hello world"));
        assertFalse(InputValidator.containsSqlInjection("user123"));
        assertFalse(InputValidator.containsSqlInjection("测试数据"));
        assertFalse(InputValidator.containsSqlInjection("normal text"));
    }

    @Test
    @DisplayName("测试 SQL 关键字 - 应该返回 true（检测到 SQL 注入）")
    void testContainsSqlInjection_SqlKeywords() {
        assertTrue(InputValidator.containsSqlInjection("SELECT * FROM users"));
        assertTrue(InputValidator.containsSqlInjection("DROP TABLE students"));
        assertTrue(InputValidator.containsSqlInjection("INSERT INTO users"));
        assertTrue(InputValidator.containsSqlInjection("DELETE FROM questions"));
        assertTrue(InputValidator.containsSqlInjection("UPDATE users SET"));
        assertTrue(InputValidator.containsSqlInjection("UNION SELECT"));
    }

    @Test
    @DisplayName("测试 SQL 注入模式 - 应该返回 true（检测到 SQL 注入）")
    void testContainsSqlInjection_SqlInjectionPatterns() {
        assertTrue(InputValidator.containsSqlInjection("' OR '1'='1"));
        assertTrue(InputValidator.containsSqlInjection("admin'--"));
        assertTrue(InputValidator.containsSqlInjection("'; DROP TABLE users"));
        assertTrue(InputValidator.containsSqlInjection("' UNION SELECT"));
        assertTrue(InputValidator.containsSqlInjection("' AND 1=1"));
        assertTrue(InputValidator.containsSqlInjection("admin'/*"));
    }

    @Test
    @DisplayName("测试 SQL 注释符 - 应该返回 true（检测到 SQL 注入）")
    void testContainsSqlInjection_SqlComments() {
        assertTrue(InputValidator.containsSqlInjection("test--comment"));
        assertTrue(InputValidator.containsSqlInjection("test/*comment*/"));
        assertTrue(InputValidator.containsSqlInjection("test#comment"));
    }

    @Test
    @DisplayName("测试 null 输入 - 应该返回 false")
    void testContainsSqlInjection_Null() {
        assertFalse(InputValidator.containsSqlInjection(null));
    }

    @Test
    @DisplayName("测试空字符串 - 应该返回 false")
    void testContainsSqlInjection_Empty() {
        assertFalse(InputValidator.containsSqlInjection(""));
        assertFalse(InputValidator.containsSqlInjection("   "));
    }

    // ==================== 测试 sanitizeInput() ====================

    @Test
    @DisplayName("测试清理正常输入 - 应该保持不变")
    void testSanitizeInput_NormalInput() {
        assertEquals("hello world", InputValidator.sanitizeInput("hello world"));
        assertEquals("user123", InputValidator.sanitizeInput("user123"));
    }

    @Test
    @DisplayName("测试清理包含单引号的输入 - 应该转义单引号")
    void testSanitizeInput_WithQuotes() {
        assertEquals("it''s a test", InputValidator.sanitizeInput("it's a test"));
        assertEquals("O''Brien", InputValidator.sanitizeInput("O'Brien"));
    }

    @Test
    @DisplayName("测试清理包含注释符的输入 - 应该移除注释符")
    void testSanitizeInput_WithComments() {
        assertEquals("testcomment", InputValidator.sanitizeInput("test--comment"));
        assertEquals("testcomment", InputValidator.sanitizeInput("test/*comment*/"));
        assertEquals("testcomment", InputValidator.sanitizeInput("test#comment"));
    }

    @Test
    @DisplayName("测试清理包含分号的输入 - 应该移除分号")
    void testSanitizeInput_WithSemicolon() {
        assertEquals("test query", InputValidator.sanitizeInput("test; query"));
    }

    @Test
    @DisplayName("测试清理 null 输入 - 应该返回 null")
    void testSanitizeInput_Null() {
        assertNull(InputValidator.sanitizeInput(null));
    }

    // ==================== 测试 isValidNumericId() ====================

    @Test
    @DisplayName("测试有效数字 ID - 应该返回 true")
    void testValidNumericId_Success() {
        assertTrue(InputValidator.isValidNumericId("123"));
        assertTrue(InputValidator.isValidNumericId("1"));
        assertTrue(InputValidator.isValidNumericId("999999"));
    }

    @Test
    @DisplayName("测试无效数字 ID - 应该返回 false")
    void testValidNumericId_Invalid() {
        assertFalse(InputValidator.isValidNumericId("abc"));
        assertFalse(InputValidator.isValidNumericId("12.34"));
        assertFalse(InputValidator.isValidNumericId(""));
    }

    @Test
    @DisplayName("测试 null ID - 应该返回 false")
    void testValidNumericId_Null() {
        assertFalse(InputValidator.isValidNumericId(null));
    }

    // ==================== 测试 isValidLength() ====================

    @Test
    @DisplayName("测试有效长度（单参数）- 应该返回 true")
    void testValidLength_MaxLength_Success() {
        assertTrue(InputValidator.isValidLength("hello", 10));
        assertTrue(InputValidator.isValidLength("test", 4));
    }

    @Test
    @DisplayName("测试超长输入（单参数）- 应该返回 false")
    void testValidLength_MaxLength_TooLong() {
        assertFalse(InputValidator.isValidLength("hello world", 5));
    }

    @Test
    @DisplayName("测试有效长度范围（双参数）- 应该返回 true")
    void testValidLength_Range_Success() {
        assertTrue(InputValidator.isValidLength("hello", 3, 10));
        assertTrue(InputValidator.isValidLength("test", 4, 4));
    }

    @Test
    @DisplayName("测试长度范围不符（双参数）- 应该返回 false")
    void testValidLength_Range_Invalid() {
        assertFalse(InputValidator.isValidLength("ab", 3, 10));
        assertFalse(InputValidator.isValidLength("hello world", 1, 5));
    }
}

package com.proveyu.student.codeground;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** The code a student is writing (table code_ground_drafts). Every query is for one student. */
@Repository
@RequiredArgsConstructor
public class CodeGroundDrafts {

    private final JdbcTemplate jdbc;

    /** language → code, for one problem. */
    public Map<String, String> of(UUID userId, String problemKey) {
        Map<String, String> drafts = new LinkedHashMap<>();
        jdbc.query("select language, code from code_ground_drafts where user_id = ? and problem_key = ?",
                rs -> {
                    drafts.put(rs.getString(1), rs.getString(2));
                }, userId.toString(), problemKey);
        return drafts;
    }

    public void save(UUID userId, String problemKey, String language, String code) {
        jdbc.update("""
                insert into code_ground_drafts (user_id, problem_key, language, code) values (?, ?, ?, ?)
                on duplicate key update code = ?""", userId.toString(), problemKey, language, code, code);
    }

    public void delete(UUID userId, String problemKey, String language) {
        jdbc.update("delete from code_ground_drafts where user_id = ? and problem_key = ? and language = ?",
                userId.toString(), problemKey, language);
    }
}

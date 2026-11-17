package com.proveyu.interview.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Finding the technologies of a role and keeping questions inside them: positive and negative cases. */
class TechStackTest {

    @Test
    void detectsTheTechnologiesOfAJobDescription() {
        String jd = "We are hiring a Java Developer (fresher). Must know Core Java, Spring Boot, Hibernate and MySQL. "
                + "Good to have: REST APIs, Git. Strong communication skills.";
        assertThat(TechStack.detect("Java Developer", jd)).containsExactly("Java", "Spring Boot", "Hibernate", "MySQL");
    }

    @Test
    void javaIsNotJavaScriptAndTheOtherWayRound() {
        assertThat(TechStack.detect("Frontend developer: JavaScript, React, HTML, CSS")).containsExactly("JavaScript", "React", "HTML", "CSS");
        assertThat(TechStack.detect("Java Script and Node.js")).containsExactly("JavaScript", "Node.js");
        assertThat(TechStack.detect("Core Java, J2EE")).containsExactly("Java", "J2EE");
    }

    @Test
    void cFamilyIsTellApart() {
        assertThat(TechStack.detect("C, C++ and C# developer")).contains("C++", "C#", "C");
        assertThat(TechStack.detect("Embedded C programming")).containsExactly("C");
        assertThat(TechStack.detect("Plan B or plan C?")).isEmpty();
    }

    @Test
    void ordinaryWordsAreNotTechnologies() {
        // react / excel / spark / node / swift as plain English
        assertThat(TechStack.detect("How do you react to feedback, excel at work, spark ideas in a team, and make swift decisions? Each node of a tree has children."))
                .isEmpty();
        assertThat(TechStack.detect("Data Analyst: Excel, Power BI, SQL")).containsExactly("SQL", "Power BI", "Excel");
    }

    @Test
    void nothingDetectedForARoleWithoutTechnologies() {
        assertThat(TechStack.detect("Software Engineer (fresher)", null)).isEmpty();
        assertThat(TechStack.detect((String) null)).isEmpty();
    }

    @Test
    void offStackFindsOtherLanguagesButAllowsTheSameFamily() {
        List<String> java = List.of("Java", "MySQL");
        assertThat(TechStack.offStack("What is a decorator in Python?", java)).isEqualTo("Python");
        assertThat(TechStack.offStack("Explain closures in JavaScript.", java)).isEqualTo("JavaScript");
        assertThat(TechStack.offStack("How do you deploy with Docker?", java)).isEqualTo("Docker");
        assertThat(TechStack.offStack("How does Spring Boot auto-configuration work?", java)).isNull();   // same family as Java
        assertThat(TechStack.offStack("How does the JVM manage memory?", java)).isNull();
        assertThat(TechStack.offStack("Write a SQL query using GROUP BY.", java)).isNull();              // MySQL brings SQL
        assertThat(TechStack.offStack("Explain the four pillars of OOP.", java)).isNull();               // general fundamentals
        assertThat(TechStack.offStack("How do you react under pressure?", java)).isNull();
    }

    @Test
    void impliedFamilies() {
        assertThat(TechStack.offStack("What is the box model in CSS?", List.of("React"))).isNull();       // web comes with JavaScript
        assertThat(TechStack.offStack("Explain Pandas groupby.", List.of("Python"))).isNull();
        assertThat(TechStack.offStack("What is a Kotlin coroutine?", List.of("Android"))).isNull();
        assertThat(TechStack.offStack("What is a Java interface?", List.of("Python"))).isEqualTo("Java");
    }

    @Test
    void noStackMeansNoRestriction() {
        assertThat(TechStack.offStack("What is a decorator in Python?", List.of())).isNull();
        assertThat(TechStack.offStack("What is a decorator in Python?", null)).isNull();
    }
}

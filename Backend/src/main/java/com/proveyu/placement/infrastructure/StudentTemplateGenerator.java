package com.proveyu.placement.infrastructure;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class StudentTemplateGenerator {

    public static final String[] HEADERS = {
            "Roll Number",
            "First Name",
            "Last Name",
            "College Email",
            "Mobile Phone",
            "Date of Birth",
            "Gender",
            "Aadhaar Number",
            "PAN Number",
            "Residential Address",
            "College Name",
            "Batch Passing Year",
            "Branch Department",
            "Degree Name",
            "10th Marks (%)",
            "12th Marks (%)",
            "Active Backlogs",
            "Education Gap Years",
            "Graduation Date",
            "Sem 1 SGPA",
            "Sem 2 SGPA",
            "Sem 3 SGPA",
            "Sem 4 SGPA",
            "Sem 5 SGPA",
            "Sem 6 SGPA",
            "Sem 7 SGPA",
            "Sem 8 SGPA",
            "Aggregate CGPA",
            "Placement Status",
            "Placed Company",
            "Placed Role",
            "Placed CTC",
            "LinkedIn Profile",
            "GitHub Profile",
            "Portfolio URL"
    };

    public byte[] generateCsvTemplate(boolean withSampleData) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {

            // UTF-8 BOM so Excel opens it with proper encoding
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);

            CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).build());

            if (withSampleData) {
                printer.printRecord(
                        "2024-CSE-001", "Rahul", "Sharma", "rahul.s@college.edu", "+91 98765 43210",
                        "2002-01-05", "Male", "5432 1098 7654", "ABCDE1234F", "Sector 19, Nerul, Navi Mumbai - 400706",
                        "MGM College of Engineering & Technology", "2024", "Computer Science & Engineering", "B.Tech",
                        "88.0", "86.0", "0", "0", "2024-06-30",
                        "8.20", "8.35", "8.40", "8.55", "8.60", "8.50", "8.65", "8.75", "8.50",
                        "Placed", "TCS", "Associate Software Engineer", "₹4.5L",
                        "https://linkedin.com/in/rahul-sharma", "https://github.com/rahul-sharma", "https://rahulsharma.dev"
                );
                printer.printRecord(
                        "2024-IT-002", "Priya", "Desai", "priya.d@college.edu", "+91 98765 43211",
                        "2002-03-12", "Female", "4321 0987 6543", "BCDEF2345G", "Vashi, Navi Mumbai - 400703",
                        "MGM College of Engineering & Technology", "2024", "Information Technology", "B.Tech",
                        "91.0", "89.0", "0", "0", "2024-06-30",
                        "8.50", "8.60", "8.70", "8.65", "8.80", "8.75", "8.90", "9.00", "8.74",
                        "Placed", "Wipro", "Project Engineer Trainee", "₹6.5L",
                        "https://linkedin.com/in/priya-desai", "https://github.com/priya-desai", ""
                );
                printer.printRecord(
                        "2024-MECH-003", "Rohan", "Verma", "rohan.v@college.edu", "+91 98765 43213",
                        "2002-07-08", "Male", "2109 8765 4321", "DEFGH4567J", "Panvel, Navi Mumbai - 410206",
                        "MGM College of Engineering & Technology", "2024", "Mechanical Engineering", "B.Tech",
                        "76.5", "74.0", "0", "1", "2024-06-30",
                        "7.10", "7.20", "7.30", "7.25", "7.40", "7.35", "7.50", "7.60", "7.34",
                        "Unplaced", "", "", "",
                        "https://linkedin.com/in/rohan-verma", "", ""
                );
            }

            printer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate student CSV template: " + e.getMessage(), e);
        }
    }
}

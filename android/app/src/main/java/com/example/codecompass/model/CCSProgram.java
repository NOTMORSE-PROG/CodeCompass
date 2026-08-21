package com.example.codecompass.model;

import java.util.List;

public class CCSProgram {

    private int id;
    private String name;
    private String abbreviation;
    private String description;

    private int durationYears;

    private List<String> specializations;

    private boolean hasBoardExam;

    private String curriculumUrl;

    public int getId() { return id; }
    public String getName() { return name; }
    public String getAbbreviation() { return abbreviation; }
    public String getDescription() { return description; }
    public int getDurationYears() { return durationYears; }
    public List<String> getSpecializations() { return specializations; }
    public boolean hasBoardExam() { return hasBoardExam; }
    public String getCurriculumUrl() { return curriculumUrl; }
}

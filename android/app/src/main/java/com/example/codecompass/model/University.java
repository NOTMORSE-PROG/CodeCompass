package com.example.codecompass.model;

import java.util.List;

public class University {

    private int id;
    private String name;
    private String abbreviation;

    private String universityType;

    private String region;
    private String city;

    private String websiteUrl;

    private String logoUrl;

    private int accreditationLevel;

    private boolean chedCoe;

    private boolean chedCod;

    private Integer tuitionRangeMin;

    private Integer tuitionRangeMax;

    private List<CCSProgram> programs;

    public int getId() { return id; }
    public String getName() { return name; }
    public String getAbbreviation() { return abbreviation; }
    public String getUniversityType() { return universityType; }
    public String getRegion() { return region; }
    public String getCity() { return city; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getLogoUrl() { return logoUrl; }
    public int getAccreditationLevel() { return accreditationLevel; }
    public boolean isChedCoe() { return chedCoe; }
    public boolean isChedCod() { return chedCod; }
    public Integer getTuitionRangeMin() { return tuitionRangeMin; }
    public Integer getTuitionRangeMax() { return tuitionRangeMax; }
    public List<CCSProgram> getPrograms() { return programs; }
}

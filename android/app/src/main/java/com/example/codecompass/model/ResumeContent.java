package com.example.codecompass.model;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class ResumeContent {

    @SerializedName("personalInfo")
    private PersonalInfo personalInfo;

    @SerializedName("summary")
    private String summary;

    @SerializedName("experience")
    private List<Experience> experience;

    @SerializedName("education")
    private List<Education> education;

    @SerializedName("skills")
    private ResumeSkills skills;

    @SerializedName("projects")
    private List<Project> projects;

    @SerializedName("certifications")
    private List<ResumeCertification> certifications;

    @SerializedName("_styling")
    private ResumeStyling styling;

    public ResumeContent() {
        this.personalInfo = new PersonalInfo();
        this.summary = "";
        this.experience = new ArrayList<>();
        this.education = new ArrayList<>();
        this.skills = new ResumeSkills();
        this.projects = new ArrayList<>();
        this.certifications = new ArrayList<>();
        this.styling = new ResumeStyling();
    }

    public PersonalInfo getPersonalInfo() { return personalInfo; }
    public void setPersonalInfo(PersonalInfo personalInfo) { this.personalInfo = personalInfo; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<Experience> getExperience() { return experience; }
    public void setExperience(List<Experience> experience) { this.experience = experience; }

    public List<Education> getEducation() { return education; }
    public void setEducation(List<Education> education) { this.education = education; }

    public ResumeSkills getSkills() { return skills; }
    public void setSkills(ResumeSkills skills) { this.skills = skills; }

    public List<Project> getProjects() { return projects; }
    public void setProjects(List<Project> projects) { this.projects = projects; }

    public List<ResumeCertification> getCertifications() { return certifications; }
    public void setCertifications(List<ResumeCertification> certifications) { this.certifications = certifications; }

    public ResumeStyling getStyling() { return styling; }
    public void setStyling(ResumeStyling styling) { this.styling = styling; }
}

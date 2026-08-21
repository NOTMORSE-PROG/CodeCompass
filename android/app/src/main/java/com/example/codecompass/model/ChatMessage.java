package com.example.codecompass.model;

import java.util.List;

public class ChatMessage {

    private String content;
    private final boolean isUser;
    private boolean isTyping;

    // Rich content attached to completed AI messages
    private List<ResourceLink>   resources;
    private List<EditProposal>   editProposals;
    private boolean              proposalsDismissed = false;
    private boolean              proposalsApplied   = false;

    private RoadmapSwitchProposal  roadmapSwitch;
    private boolean                switchDismissed  = false;
    private boolean                switchApplied    = false;

    private RoadmapUpskillProposal roadmapUpskill;
    private boolean                upskillDismissed = false;
    private boolean                upskillApplied   = false;

    public ChatMessage(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.isTyping = false;
    }

    /** Creates a typing-indicator placeholder (AI side, no content yet). */
    public ChatMessage(boolean isTyping) {
        this.content = "";
        this.isUser = false;
        this.isTyping = isTyping;
    }

    public String getContent()  { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isUser()     { return isUser; }
    public boolean isTyping()   { return isTyping; }
    public void setTyping(boolean typing) { this.isTyping = typing; }

    public List<ResourceLink> getResources()             { return resources; }
    public void setResources(List<ResourceLink> r)       { this.resources = r; }

    public List<EditProposal> getEditProposals()         { return editProposals; }
    public void setEditProposals(List<EditProposal> p)   { this.editProposals = p; }

    public boolean isProposalsDismissed()                { return proposalsDismissed; }
    public void setProposalsDismissed(boolean v)         { this.proposalsDismissed = v; }

    public boolean isProposalsApplied()                  { return proposalsApplied; }
    public void setProposalsApplied(boolean v)           { this.proposalsApplied = v; }

    public RoadmapSwitchProposal getRoadmapSwitch()        { return roadmapSwitch; }
    public void setRoadmapSwitch(RoadmapSwitchProposal p)  { this.roadmapSwitch = p; }
    public boolean isSwitchDismissed()                     { return switchDismissed; }
    public void setSwitchDismissed(boolean v)              { this.switchDismissed = v; }
    public boolean isSwitchApplied()                       { return switchApplied; }
    public void setSwitchApplied(boolean v)                { this.switchApplied = v; }

    public RoadmapUpskillProposal getRoadmapUpskill()         { return roadmapUpskill; }
    public void setRoadmapUpskill(RoadmapUpskillProposal p)   { this.roadmapUpskill = p; }
    public boolean isUpskillDismissed()                       { return upskillDismissed; }
    public void setUpskillDismissed(boolean v)                { this.upskillDismissed = v; }
    public boolean isUpskillApplied()                         { return upskillApplied; }
    public void setUpskillApplied(boolean v)                  { this.upskillApplied = v; }
}

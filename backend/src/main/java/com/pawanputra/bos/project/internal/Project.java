package com.pawanputra.bos.project.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_number", nullable = false, length = 50)
    private String projectNumber;

    @Column(name = "title", nullable = false, length = 250)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "service_name", length = 255)
    private String serviceName;

    @Column(name = "project_manager_id", nullable = false)
    private UUID projectManagerId;

    @Column(name = "project_manager_name", nullable = false, length = 255)
    private String projectManagerName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expected_completion_date", nullable = false)
    private LocalDate expectedCompletionDate;

    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "priority", nullable = false, length = 30)
    private String priority;

    @Column(name = "budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(name = "revenue", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenue;

    @Column(name = "cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(name = "material_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal materialCost = BigDecimal.ZERO;

    @Column(name = "labour_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal labourCost = BigDecimal.ZERO;

    @Column(name = "vendor_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal vendorCost = BigDecimal.ZERO;

    @Column(name = "travel_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal travelCost = BigDecimal.ZERO;

    @Column(name = "other_expenses", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherExpenses = BigDecimal.ZERO;

    @Column(name = "expected_cost", nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedCost = BigDecimal.ZERO;

    @Column(name = "progress_percentage", nullable = false)
    private int progressPercentage;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProjectMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<Milestone> milestones = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<ProjectTask> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dueDate ASC")
    private List<Deliverable> deliverables = new ArrayList<>();

    protected Project() {
    }

    public Project(
            UUID organizationId,
            String projectNumber,
            String title,
            String description,
            UUID customerId,
            String customerName,
            UUID orderId,
            String orderNumber,
            UUID serviceId,
            String serviceName,
            UUID projectManagerId,
            String projectManagerName,
            LocalDate startDate,
            LocalDate expectedCompletionDate,
            String status,
            String priority,
            BigDecimal budget,
            BigDecimal revenue,
            String actor) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.projectNumber = projectNumber;
        this.title = title;
        this.description = description;
        this.customerId = customerId;
        this.customerName = customerName;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.projectManagerId = projectManagerId;
        this.projectManagerName = projectManagerName;
        this.startDate = startDate;
        this.expectedCompletionDate = expectedCompletionDate;
        this.status = status != null ? status : "PLANNING";
        this.priority = priority != null ? priority : "MEDIUM";
        this.budget = budget != null ? budget : BigDecimal.ZERO;
        this.revenue = revenue != null ? revenue : BigDecimal.ZERO;
        this.cost = BigDecimal.ZERO;
        this.progressPercentage = 0;
        this.createdAt = Instant.now();
        this.createdBy = actor;
        this.updatedAt = this.createdAt;
        this.updatedBy = actor;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getProjectNumber() {
        return projectNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public void setOrderId(UUID orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public UUID getProjectManagerId() {
        return projectManagerId;
    }

    public void setProjectManagerId(UUID projectManagerId) {
        this.projectManagerId = projectManagerId;
    }

    public String getProjectManagerName() {
        return projectManagerName;
    }

    public void setProjectManagerName(String projectManagerName) {
        this.projectManagerName = projectManagerName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getExpectedCompletionDate() {
        return expectedCompletionDate;
    }

    public void setExpectedCompletionDate(LocalDate expectedCompletionDate) {
        this.expectedCompletionDate = expectedCompletionDate;
    }

    public LocalDate getActualCompletionDate() {
        return actualCompletionDate;
    }

    public void setActualCompletionDate(LocalDate actualCompletionDate) {
        this.actualCompletionDate = actualCompletionDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost != null ? cost : BigDecimal.ZERO;
    }

    public BigDecimal getMaterialCost() {
        return materialCost;
    }

    public void setMaterialCost(BigDecimal materialCost) {
        this.materialCost = materialCost != null ? materialCost : BigDecimal.ZERO;
    }

    public BigDecimal getLabourCost() {
        return labourCost;
    }

    public void setLabourCost(BigDecimal labourCost) {
        this.labourCost = labourCost != null ? labourCost : BigDecimal.ZERO;
    }

    public BigDecimal getVendorCost() {
        return vendorCost;
    }

    public void setVendorCost(BigDecimal vendorCost) {
        this.vendorCost = vendorCost != null ? vendorCost : BigDecimal.ZERO;
    }

    public BigDecimal getTravelCost() {
        return travelCost;
    }

    public void setTravelCost(BigDecimal travelCost) {
        this.travelCost = travelCost != null ? travelCost : BigDecimal.ZERO;
    }

    public BigDecimal getOtherExpenses() {
        return otherExpenses;
    }

    public void setOtherExpenses(BigDecimal otherExpenses) {
        this.otherExpenses = otherExpenses != null ? otherExpenses : BigDecimal.ZERO;
    }

    public BigDecimal getExpectedCost() {
        return expectedCost;
    }

    public void setExpectedCost(BigDecimal expectedCost) {
        this.expectedCost = expectedCost != null ? expectedCost : BigDecimal.ZERO;
    }

    public void recalculateActualCost() {
        this.cost = (this.materialCost != null ? this.materialCost : BigDecimal.ZERO)
                .add(this.labourCost != null ? this.labourCost : BigDecimal.ZERO)
                .add(this.vendorCost != null ? this.vendorCost : BigDecimal.ZERO)
                .add(this.travelCost != null ? this.travelCost : BigDecimal.ZERO)
                .add(this.otherExpenses != null ? this.otherExpenses : BigDecimal.ZERO);
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(int progressPercentage) {
        this.progressPercentage = Math.max(0, Math.min(100, progressPercentage));
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public long getVersion() {
        return version;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public List<ProjectMember> getMembers() {
        return members;
    }

    public void addMember(ProjectMember member) {
        this.members.add(member);
        member.setProject(this);
    }

    public List<Milestone> getMilestones() {
        return milestones;
    }

    public void addMilestone(Milestone milestone) {
        this.milestones.add(milestone);
        milestone.setProject(this);
    }

    public List<ProjectTask> getTasks() {
        return tasks;
    }

    public void addTask(ProjectTask task) {
        this.tasks.add(task);
        task.setProject(this);
    }

    public List<Deliverable> getDeliverables() {
        return deliverables;
    }

    public void addDeliverable(Deliverable deliverable) {
        this.deliverables.add(deliverable);
        deliverable.setProject(this);
    }
}

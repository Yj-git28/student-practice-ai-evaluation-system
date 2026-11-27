package org.example.backend.system.entity;

public class SysRole {
    private Long roleId;
    private String roleName;
    private String roleCode;
    /** 数据范围：1 全部 / 2 本部门 / 3 本人 */
    private Integer dataScope;
    /** 状态：1 启用 0 停用 */
    private Integer status;
    /** 是否内置角色：1 内置（不可删除、不可改角色码） 0 自定义 */
    private Integer builtin;
    private String description;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Integer getDataScope() { return dataScope; }
    public void setDataScope(Integer dataScope) { this.dataScope = dataScope; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getBuiltin() { return builtin; }
    public void setBuiltin(Integer builtin) { this.builtin = builtin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

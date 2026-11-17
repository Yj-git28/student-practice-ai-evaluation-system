package org.example.backend.system.entity;

public class SysRole {
    private Long roleId;
    private String roleName;
    private String roleCode;
    /** 数据范围：1 全部 / 2 本部门 / 3 本人 */
    private Integer dataScope;

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Integer getDataScope() { return dataScope; }
    public void setDataScope(Integer dataScope) { this.dataScope = dataScope; }
}

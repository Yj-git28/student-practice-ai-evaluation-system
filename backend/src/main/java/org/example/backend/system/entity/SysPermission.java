package org.example.backend.system.entity;

import java.util.ArrayList;
import java.util.List;

public class SysPermission {
    private Long permissionId;
    private Long parentId;
    private String permName;
    private Integer permType;
    private String permCode;
    private String permPath;
    private String icon;
    private Integer sort;

    /** 非表字段，仅用于菜单树 */
    private List<SysPermission> children = new ArrayList<>();

    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getPermName() { return permName; }
    public void setPermName(String permName) { this.permName = permName; }
    public Integer getPermType() { return permType; }
    public void setPermType(Integer permType) { this.permType = permType; }
    public String getPermCode() { return permCode; }
    public void setPermCode(String permCode) { this.permCode = permCode; }
    public String getPermPath() { return permPath; }
    public void setPermPath(String permPath) { this.permPath = permPath; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public List<SysPermission> getChildren() { return children; }
    public void setChildren(List<SysPermission> children) { this.children = children; }
}

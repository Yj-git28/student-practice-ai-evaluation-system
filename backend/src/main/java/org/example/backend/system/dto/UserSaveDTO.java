package org.example.backend.system.dto;

import java.util.List;

/** 管理员新增/编辑用户、批量导入的入参 */
public class UserSaveDTO {

    private String username;
    private String realName;
    private String email;
    private String password;
    /** 兼容旧前端/导入的单角色主角色 ID */
    private Integer roleId;
    /** 多角色 ID 列表（优先于 roleId） */
    private List<Long> roleIds;
    private Long classId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Integer getRoleId() { return roleId; }
    public void setRoleId(Integer roleId) { this.roleId = roleId; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}

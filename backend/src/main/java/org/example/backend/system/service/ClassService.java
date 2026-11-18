package org.example.backend.system.service;

import org.example.backend.common.BizException;
import org.example.backend.system.dto.ClassSaveDTO;
import org.example.backend.system.mapper.ClassMapper;
import org.springframework.stereotype.Service;

/** 班级管理：新增、修改（含年级）、删除 */
@Service
public class ClassService {

    private final ClassMapper classMapper;

    public ClassService(ClassMapper classMapper) {
        this.classMapper = classMapper;
    }

    public void create(ClassSaveDTO dto) {
        String className = validateName(dto.getClassName());
        if (classMapper.existsByName(className, null)) {
            throw new BizException(4001, "班级已存在：" + className);
        }
        classMapper.insert(className, trim(dto.getGrade()));
    }

    public void update(Long classId, ClassSaveDTO dto) {
        if (!classMapper.exists(classId)) {
            throw new BizException(4004, "班级不存在");
        }
        String className = validateName(dto.getClassName());
        if (classMapper.existsByName(className, classId)) {
            throw new BizException(4001, "班级名称已被占用：" + className);
        }
        classMapper.update(classId, className, trim(dto.getGrade()));
    }

    public void delete(Long classId) {
        if (!classMapper.exists(classId)) {
            throw new BizException(4004, "班级不存在");
        }
        int users = classMapper.countUsers(classId);
        if (users > 0) {
            throw new BizException(4003, "该班级下还有 " + users + " 个账号，请先调整这些账号的班级");
        }
        classMapper.deleteById(classId);
    }

    private String validateName(String className) {
        String name = trim(className);
        if (name.isEmpty() || name.length() > 64) {
            throw new BizException(4002, "班级名称不能为空且不超过 64 字");
        }
        return name;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

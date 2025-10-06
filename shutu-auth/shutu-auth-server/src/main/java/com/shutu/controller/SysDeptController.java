package com.shutu.controller;

import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.AssertUtils;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.commons.tools.validator.group.AddGroup;
import com.shutu.commons.tools.validator.group.DefaultGroup;
import com.shutu.commons.tools.validator.group.UpdateGroup;
import com.shutu.domain.dto.SysDeptDTO;
import com.shutu.service.SysDeptService;
import com.shutu.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;

/**
 * 部门管理
 */
@RestController
@RequestMapping("dept")
@Tag(name = "部门管理")
public class SysDeptController {
    @Resource
    private SysDeptService sysDeptService;
    @Resource
    private SysUserService sysUserService;

    @GetMapping("list")
    @Operation(summary = "列表")
    @PreAuthorize("hasAuthority('sys:dept:list')")
    public Result<List<SysDeptDTO>> list() {
        List<SysDeptDTO> list = sysDeptService.list(new HashMap<>(1));

        return new Result<List<SysDeptDTO>>().ok(list);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('sys:dept:info')")
    public Result<SysDeptDTO> get(@PathVariable("id") Long id) {
        SysDeptDTO data = sysDeptService.get(id);

        if (data.getLeaderId() != null) {
            data.setLeaderName(sysUserService.get(data.getLeaderId()).getRealName());
        }

        return new Result<SysDeptDTO>().ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    //@LogOperation("Save Dept")
    @PreAuthorize("hasAuthority('sys:dept:save')")
    public Result save(@RequestBody SysDeptDTO dto) throws Exception {
        //效验数据
        ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class);

        sysDeptService.save(dto);

        return new Result();
    }

    @PutMapping
    @Operation(summary = "修改")
   // @LogOperation("Update Dept")
    @PreAuthorize("hasAuthority('sys:dept:update')")
    public Result update(@RequestBody SysDeptDTO dto) throws Exception {
        //效验数据
        ValidatorUtils.validateEntity(dto, UpdateGroup.class, DefaultGroup.class);

        sysDeptService.update(dto);

        return new Result();
    }

    @DeleteMapping("{id}")
    @Operation(summary = "删除")
   // @LogOperation("Delete Dept")
    @PreAuthorize("hasAuthority('sys:dept:delete')")
    public Result delete(@PathVariable("id") Long id) {
        //效验数据
        AssertUtils.isNull(id, "id");

        sysDeptService.delete(id);

        return new Result();
    }
}

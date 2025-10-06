package com.shutu.controller;


import com.shutu.commons.tools.constant.Constant;
import com.shutu.commons.tools.page.PageData;
import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.AssertUtils;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.commons.tools.validator.group.AddGroup;
import com.shutu.commons.tools.validator.group.DefaultGroup;
import com.shutu.commons.tools.validator.group.UpdateGroup;
import com.shutu.domain.dto.SysRoleDTO;
import com.shutu.service.SysRoleDataScopeService;
import com.shutu.service.SysRoleMenuService;
import com.shutu.service.SysRoleService;
import com.shutu.service.SysRoleUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理
 */
@RestController
@RequestMapping("role")
@Tag(name = "角色管理")
public class SysRoleController {
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Resource
    private SysRoleDataScopeService sysRoleDataScopeService;
    @Resource
    private SysRoleUserService sysRoleUserService;

    @GetMapping("page")
    @Operation(summary = "分页")
    @Parameters({
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
            @Parameter(name = Constant.ORDER_FIELD, description = "排序字段"),
            @Parameter(name = Constant.ORDER, description = "排序方式，可选值(asc、desc)"),
            @Parameter(name = "name", description = "角色名")
    })
    @PreAuthorize("hasAuthority('sys:role:page')")
    public Result<PageData<SysRoleDTO>> page(@Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        PageData<SysRoleDTO> page = sysRoleService.page(params);

        return new Result<PageData<SysRoleDTO>>().ok(page);
    }

    @GetMapping("list")
    @Operation(summary = "列表")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public Result<List<SysRoleDTO>> list() {
        List<SysRoleDTO> data = sysRoleService.list(new HashMap<>(1));

        return new Result<List<SysRoleDTO>>().ok(data);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('sys:role:info')")
    public Result<SysRoleDTO> get(@PathVariable("id") Long id) {
        SysRoleDTO data = sysRoleService.get(id);

        //查询角色对应的菜单
        List<Long> menuIdList = sysRoleMenuService.getMenuIdList(id);
        data.setMenuIdList(menuIdList);

        //查询角色对应的数据权限
        List<Long> deptIdList = sysRoleDataScopeService.getDeptIdList(id);
        data.setDeptIdList(deptIdList);

        return new Result<SysRoleDTO>().ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    //@LogOperation("Save Role")
    @PreAuthorize("hasAuthority('sys:role:save')")
    public Result save(@RequestBody SysRoleDTO dto) throws Exception {
        //效验数据
        ValidatorUtils.validateEntity(dto, AddGroup.class, DefaultGroup.class);

        sysRoleService.save(dto);

        return new Result();
    }

    @PutMapping
    @Operation(summary = "修改")
   // @LogOperation("Update Role")
    @PreAuthorize("hasAuthority('sys:role:update')")
    public Result update(@RequestBody SysRoleDTO dto) throws Exception {
        //效验数据
        ValidatorUtils.validateEntity(dto, UpdateGroup.class, DefaultGroup.class);

        sysRoleService.update(dto);

        return new Result();
    }

    @DeleteMapping
    @Operation(summary = "删除")
   // @LogOperation("Delete Role")
    @PreAuthorize("hasAuthority('sys:role:delete')")
    public Result delete(@RequestBody Long[] ids) {
        //效验数据
        AssertUtils.isArrayEmpty(ids, "id");

        sysRoleService.delete(ids);

        return new Result();
    }

    @GetMapping("getRoleIdList")
    public Result<List<Long>> getRoleIdList(Long userId) {
        //用户角色列表
        List<Long> roleIdList = sysRoleUserService.getRoleIdList(userId);

        return new Result<List<Long>>().ok(roleIdList);
    }

}
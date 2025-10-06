package com.shutu.controller;


import com.shutu.commons.security.user.SecurityUser;
import com.shutu.commons.tools.exception.ErrorCode;
import com.shutu.commons.tools.utils.Result;
import com.shutu.commons.tools.validator.AssertUtils;
import com.shutu.commons.tools.validator.ValidatorUtils;
import com.shutu.commons.tools.validator.group.DefaultGroup;
import com.shutu.domain.dto.SysMenuDTO;
import com.shutu.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

/**
 * 菜单管理
 */
@RestController
@RequestMapping("menu")
@Tag(name = "菜单管理")
public class SysMenuController {
    @Resource
    private SysMenuService sysMenuService;

    @GetMapping("nav")
    @Operation(summary = "导航")
    public Result<List<SysMenuDTO>> nav() {
        List<SysMenuDTO> list = sysMenuService.getUserMenuNavList(SecurityUser.getUser());


        return new Result<List<SysMenuDTO>>().ok(list);
    }

    @GetMapping("permissions")
    @Operation(summary = "权限标识")
    public Result<Set<String>> permissions() {
        Set<String> set = sysMenuService.getUserPermissions(SecurityUser.getUser());

        return new Result<Set<String>>().ok(set);
    }

    @GetMapping("list")
    @Operation(summary = "列表")
    @Parameter(name = "type", description = "菜单类型 0：菜单 1：按钮  null：全部")
    public Result<List<SysMenuDTO>> list(Integer type) {
        List<SysMenuDTO> list = sysMenuService.getMenuList(type);

        return new Result<List<SysMenuDTO>>().ok(list);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('sys:menu:info')")
    public Result<SysMenuDTO> get(@PathVariable("id") Long id) {
        SysMenuDTO data = sysMenuService.get(id);

        return new Result<SysMenuDTO>().ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    //@LogOperation("Save Menu")
    @PreAuthorize("hasAuthority('sys:menu:save')")
    public Result save(@RequestBody SysMenuDTO dto) throws Exception {
        //效验数据
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);

        sysMenuService.save(dto);

        return new Result();
    }

    @PutMapping
    @Operation(summary = "修改")
  //  @LogOperation("Update Menu")
    @PreAuthorize("hasAuthority('sys:menu:update')")
    public Result update(@RequestBody SysMenuDTO dto) throws Exception {
        //效验数据
        ValidatorUtils.validateEntity(dto, DefaultGroup.class);

        sysMenuService.update(dto);

        return new Result();
    }

    @DeleteMapping("{id}")
    @Operation(summary = "删除")
   // @LogOperation("Delete Menu")
    @PreAuthorize("hasAuthority('sys:menu:delete')")
    public Result delete(@PathVariable("id") Long id) {
        //效验数据
        AssertUtils.isNull(id, "id");

        //判断是否有子菜单或按钮
        List<SysMenuDTO> list = sysMenuService.getListPid(id);
        if (list.size() > 0) {
            return new Result().error(ErrorCode.SUB_MENU_EXIST);
        }

        sysMenuService.delete(id);

        return new Result();
    }

    @GetMapping("select")
    @Operation(summary = "角色菜单权限")
    @PreAuthorize("hasAuthority('sys:menu:select')")
    public Result<List<SysMenuDTO>> select() {
        List<SysMenuDTO> list = sysMenuService.getUserMenuList(SecurityUser.getUser(), null);

        return new Result<List<SysMenuDTO>>().ok(list);
    }

}
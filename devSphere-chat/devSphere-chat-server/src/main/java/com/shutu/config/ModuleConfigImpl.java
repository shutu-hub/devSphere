package com.shutu.config;


import com.shutu.commons.tools.config.ModuleConfig;
import org.springframework.stereotype.Service;


/**
 * 模块配置信息
 */
@Service
public class ModuleConfigImpl implements ModuleConfig {
    @Override
    public String getName() {
        return "chat";
    }
}
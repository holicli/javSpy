package org.holic.javspy.javbusapi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面入口：前端为 Vue SPA（构建产物输出到 static/），
 * 根路径由 Spring Boot 的欢迎页自动指向 static/index.html；
 * 旧地址 /javbus-api/view 重定向到首页。
 */
@Controller
public class JavbusApiPageController {

    /** 旧入口兼容：GET /javbus-api/view -> 首页 SPA */
    @GetMapping("/javbus-api/view")
    public String view() {
        return "redirect:/";
    }
}

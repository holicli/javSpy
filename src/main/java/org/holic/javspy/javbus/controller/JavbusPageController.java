package org.holic.javspy.javbus.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * javbus 影片表格页面。
 */
@Controller
public class JavbusPageController {

    /** 影片列表页面：GET /javbus/view */
    @GetMapping("/javbus/view")
    public String view() {
        return "javbus";
    }
}

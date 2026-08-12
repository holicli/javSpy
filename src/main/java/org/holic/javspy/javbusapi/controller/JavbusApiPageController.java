package org.holic.javspy.javbusapi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * javbus API 影片页面。
 */
@Controller
public class JavbusApiPageController {

    /** 影片列表页面：GET /javbus-api/view */
    @GetMapping("/javbus-api/view")
    public String view() {
        return "javbusapi";
    }
}

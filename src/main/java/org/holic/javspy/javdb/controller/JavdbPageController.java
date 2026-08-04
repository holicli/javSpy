package org.holic.javspy.javdb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * javdb 影片表格页面。
 */
@Controller
public class JavdbPageController {

    /** 首页影片表格：GET /javdb/view */
    @GetMapping("/javdb/view")
    public String view() {
        return "javdb";
    }
}

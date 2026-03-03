/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.holic.javspy.controller;

import org.holic.javspy.misc.ResultPage;
import org.holic.javspy.model.NewMovie;
import org.holic.javspy.service.JavService;
import org.holic.javspy.service.MovieApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:chenxilzx1@gmail.com">theonefx</a>
 */
@Controller
public class PathVariableController {

    @Value("${conf.qbittorrent.qbtUrl}")
    String qbtUrl;
    @Value("${conf.qbittorrent.username}")
    String username;
    @Value("${conf.qbittorrent.password}")
    String password;

    @Autowired
    private JavService javService;
    @Autowired
    private MovieApiService movieApiService;

    @RequestMapping("/")
    public String getHomePage(){
        return "newmovie";
    }

    // http://127.0.0.1:8080/javabeat/somewords
    @RequestMapping(value = "/javabeat/somewords", method = RequestMethod.GET)
    @ResponseBody
    public String getRegExp() {
        return "qbtUrl" + qbtUrl+",username"+username+",password"+password;
    }

    @RequestMapping(value = "/javabeat/getfromdb", method = RequestMethod.GET)
    @ResponseBody
    public Integer getfromdb() {
        return javService.getMovie();
    }

    @RequestMapping(value = "/getNewMovie", method = RequestMethod.GET)
    @ResponseBody
    public ResultPage<List<NewMovie>> getNewMovie(@RequestParam("page") Integer page, @RequestParam("size") String size) throws IOException {
        return ResultPage.ok(javService.getNewMovie(page));
    }
}

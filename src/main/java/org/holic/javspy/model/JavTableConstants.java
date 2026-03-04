package org.holic.javspy.model;

import java.util.HashMap;
import java.util.Map;

public class JavTableConstants {


	/**
	 * 成功标记
	 */
	public static final Integer SUCCESS = 200;
	public static final String SUCCESS_MSG = "success";

	/**
	 * 失败标记
	 */
	public static final Integer FAIL = 500;
	public static final String FAIL_MSG = "未知异常，请联系管理员";

	//数据字典类型维护根目录
	public static final String TABLE_DIRECTOR = "avbook_avmoo_director";  //导演表
	public static final String TABLE_GENER = "avbook_avmoo_genre";  //类型表
	public static final String TABLE_LABEL = "avbook_avmoo_label";  //系列表
	public static final String TABLE_SERIES = "avbook_avmoo_series";  //管控模式
	public static final String TABLE_STAR = "avbook_avmoo_star";  //管控模式
	public static final String TABLE_STUDIO = "avbook_avmoo_studio";  //管控模式


	// 这些信息表相对简单 busid（code_36），name，libid（暂时不存），获取表对应的name
	public static Map<String,String> getTableValueName()
	{
		Map map = new HashMap();
		map.put(TABLE_DIRECTOR, "director_name");
		map.put(TABLE_GENER, "genre_desc");
		map.put(TABLE_LABEL, "label_name");
		map.put(TABLE_SERIES, "series_name");
		map.put(TABLE_STAR, "star_name");
		map.put(TABLE_STUDIO, "studio_name");
		return map;
	}
}

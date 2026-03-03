package org.holic.javspy.misc;

import com.github.pagehelper.PageInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.holic.javspy.model.JavTableConstants;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class ResultPage<T> implements Serializable {

    private static final long serialVersionUID = -2080637444818531454L;

    public static final int SUCCESS = JavTableConstants.SUCCESS;
    public static final String SUCCESS_MSG = JavTableConstants.SUCCESS_MSG;

    public static final int FAIL = JavTableConstants.FAIL;
    public static final String FAIL_MSG = JavTableConstants.FAIL_MSG;

    private int code;
    private String msg;
    private PageInfo<T> data;

    public ResultPage(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static ResultPage ok(){
        return ok(SUCCESS_MSG);
    }

    public static ResultPage ok(String msg) {
        return new ResultPage(SUCCESS, msg);
    }

    public static ResultPage ok(PageInfo pageInfo) {
        return new ResultPage(SUCCESS, SUCCESS_MSG, pageInfo);
    }

    public static <T> ResultPage ok(List<T> dataList) {
        PageInfo<T> pageInfo = new PageInfo<>(dataList);
        return ok(pageInfo);
    }

    public static ResultPage fail(){
        return fail(FAIL_MSG);
    }

    public static ResultPage fail(String msg){
        return new ResultPage(FAIL, msg);
    }


}

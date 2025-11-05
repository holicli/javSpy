package org.holic.javspy.misc;

import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 返回结果对象
 * @author 孙修瑞
 * @param <T>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class WebResult<T> {

    public interface IWithSuccess {}
    public interface IWithoutData extends IWithSuccess {}

    /** 操作结果是否成功 */
    @JsonView(IWithSuccess.class)
    private Boolean success;

    /** 返回数据 */
    private T data;

    /** 操作返回消息内容 */
    @JsonView(IWithoutData.class)
    private String message;

    public WebResult(Boolean success) {
        this.success = success;
    }

    public WebResult(Boolean success, String message) {
        this(success);
        this.message = message;
    }

    public String getMessage() {
        if (StringUtils.isBlank(message)) {
            return Objects.equals(success, Boolean.TRUE) ? "操作成功！" : "操作失败！";
        }
        return message;
    }

    public Boolean failed(){
        if(Objects.isNull(getSuccess())) {
            return true;
        }
        return !getSuccess();
    }
}

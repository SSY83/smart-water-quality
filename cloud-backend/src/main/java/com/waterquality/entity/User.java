package com.waterquality.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {
    private String username;
    private String password;
    private String phone;
    private String email;
    private String role;
    private Integer status;

    public boolean isAdmin() {
        return "admin".equals(this.role);
    }

    public boolean isValid() {
        return Integer.valueOf(1).equals(this.status);
    }
}

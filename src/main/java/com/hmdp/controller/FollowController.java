package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result<Boolean> follow(@PathVariable("id") Long followUserId, @PathVariable Boolean isFollow) {
        return Result.ok(followService.follow(followUserId, isFollow));
    }

    @GetMapping("/or/not/{id}")
    public Result<Boolean> isFollow(@PathVariable("id") Long followUserId) {
        return Result.ok(followService.isFollow(followUserId));
    }

    @GetMapping("/common/{id}")
    public Result<List<UserDTO>> followCommons(@PathVariable Long id){
        return Result. ok(followService.followCommons(id));
    }

}

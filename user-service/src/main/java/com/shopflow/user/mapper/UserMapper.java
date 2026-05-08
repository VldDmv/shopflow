package com.shopflow.user.mapper;

import com.shopflow.user.dto.UserResponse;
import com.shopflow.user.entity.Role;
import com.shopflow.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @org.mapstruct.Mapping(target = "role", source = "role", qualifiedByName = "roleToString")
    UserResponse toResponse(User user);

    @Named("roleToString")
    default String roleToString(Role role) {
        return role == null ? null : role.name();
    }
}
package com.english.education.model.service.users;

import com.english.education.exception.CustomException;
import com.english.education.model.dto.request.user.ChangeAvatarRequest;
import com.english.education.model.dto.request.user.ChangePasswordRequest;
import com.english.education.model.dto.request.user.UpdateUserRequest;
import com.english.education.model.entity.User;
import com.english.education.model.enums.RoleName;
import com.english.education.security.principle.UserDetailCustom;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.Set;


public interface UserService {

    ResponseEntity<?> listUser(Pageable pageable, String search, String status, Set<RoleName> roles) throws CustomException;

    User findById(Integer id);

    ResponseEntity<?> lockUser(Integer userId);

    ResponseEntity<?> unlockUser(Integer userId);

    ResponseEntity<?> detail(Integer userId);

    ResponseEntity<?> updateUser(UpdateUserRequest request, UserDetailCustom userDetailCustom) throws CustomException;

    ResponseEntity<?> changePassword(ChangePasswordRequest request, UserDetailCustom userDetailCustom) throws CustomException;

    ResponseEntity<?> changeAvatar(ChangeAvatarRequest request, UserDetailCustom userDetailCustom) throws CustomException;

    ResponseEntity<?> deleteUser(Integer userId) throws CustomException;
}

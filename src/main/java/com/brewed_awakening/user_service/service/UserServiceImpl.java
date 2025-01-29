package com.brewed_awakening.user_service.service;

import com.brewed_awakening.user_service.data.AppUserRepository;
import com.brewed_awakening.user_service.domain.AppUser;
import com.brewed_awakening.user_service.domain.OrderResponseModel;
import com.brewed_awakening.user_service.domain.UserDto;
import com.brewed_awakening.user_service.utils.JwtUtil;
import com.brewed_awakening.user_service.utils.UsersServiceException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.springframework.security.core.userdetails.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {
    BCryptPasswordEncoder bCryptPasswordEncoder;
    AppUserRepository usersRepository;
    JwtUtil jwtUtil;
    RestTemplate restTemplate;
    Environment environment;
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public UserServiceImpl(BCryptPasswordEncoder bCryptPasswordEncoder, AppUserRepository usersRepository,
                            JwtUtil jwtUtil, RestTemplate restTemplate, Environment environment) {
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.usersRepository = usersRepository;
        this.jwtUtil = jwtUtil;
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @Override
    public UserDto createUser(String username, String password) {
        AppUser userEntity = new AppUser();
        userEntity.setUsername(username);
        userEntity.setUserId(UUID.randomUUID().toString());
        userEntity.setPassword(bCryptPasswordEncoder.encode(password));
        usersRepository.save(userEntity);

        UserDto userDto = new UserDto();
        userDto.setUserId(userEntity.getUserId());
        userDto.setUsername(userEntity.getUsername());
        userDto.setPassword(userEntity.getPassword());

        return userDto;
    }

    @Override
    public UserDto getUserByUsername(String username) {
        AppUser userEntity = usersRepository.findByUsername(username);

        if (userEntity == null) {
            throw new UsernameNotFoundException(username);
        }

        UserDto obtainedUser = new UserDto();
        obtainedUser.setUserId(userEntity.getUserId());
        obtainedUser.setUsername(userEntity.getUsername());
        obtainedUser.setPassword(userEntity.getPassword());

        return obtainedUser;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser userEntity = usersRepository.findByUsername(username);

        if (userEntity == null) {
            throw new UsernameNotFoundException(username);
        }

        return new User(userEntity.getUsername(), userEntity.getPassword(), true, // Email verification status
                true, true, true, new ArrayList<>());
    }

    @Override
    public void deleteUser(String userId, String authorizationHeader) {

        String userIdFromHeader = jwtUtil.getUserId(authorizationHeader);

        if (!userId.equalsIgnoreCase(userIdFromHeader)) {
            throw new UsersServiceException("Operation not allowed");
        }

        AppUser userEntity = usersRepository.findByUserId(userId);

        if (userEntity == null)
            throw new UsersServiceException("User not found");

        usersRepository.delete(userEntity);

    }

    @Override
    public UserDto getUserByUserId(String userId) throws UsersServiceException {
        AppUser userEntity = usersRepository.findByUserId(userId);

        if (userEntity == null)
            throw new UsersServiceException(environment.getProperty("users.exceptions.user-not-found"));

        UserDto obtainedUser = new UserDto();
        obtainedUser.setUserId(userEntity.getUserId());
        obtainedUser.setUsername(userEntity.getUsername());
        obtainedUser.setPassword(userEntity.getPassword());

        return obtainedUser;
    }

    @Override
    public List<UserDto> getUsers() {
        List<AppUser> userEntities = (List<AppUser>) usersRepository.findAll();

        if (userEntities == null || userEntities.isEmpty())
            return new ArrayList<>();

        List<UserDto> returnValue = new ArrayList<>();

        userEntities.forEach(
                (el) -> {
                    UserDto tempUser = new UserDto();
                    tempUser.setUserId(el.getUserId());
                    tempUser.setUsername(el.getUsername());
                    tempUser.setPassword(el.getPassword());
                    returnValue.add(tempUser);
                }
        );

        return returnValue;
    }


    @Override
    public List<OrderResponseModel> getUserOrders(String jwt) {

        String ordersUrl = environment.getProperty("orders.url");
        logger.info("ordersUrl = " + ordersUrl);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", "Bearer " + jwt);
        httpHeaders.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        ResponseEntity<List<OrderResponseModel>> ordersListResponse = restTemplate.exchange(ordersUrl, HttpMethod.GET,
                new HttpEntity<>(httpHeaders), new ParameterizedTypeReference<List<OrderResponseModel>>() {
                });

        logger.info(
                "Orders web service endpoint called and recieved " + ordersListResponse.getBody().size() + " items");

        return ordersListResponse.getBody();
    }

}

package ru.practicum.ewm.main.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.main.dto.Dtos.*;
import ru.practicum.ewm.main.model.*;
import ru.practicum.ewm.main.repository.*;
import ru.practicum.ewm.main.mapper.DtoMapper;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository users;
    private final EntityResolver entities;
    private final DtoMapper mapper;

    public UserService(UserRepository users, EntityResolver entities, DtoMapper mapper) {
        this.users = users;
        this.entities = entities;
        this.mapper = mapper;
    }

    public List<UserDto> getUsers(List<Long> ids, int from, int size) {
        List<UserEntity> all = ids == null || ids.isEmpty() ? users.findAll() : users.findAllById(ids);
        return Pagination.slice(all, from, size).stream().map(mapper::userDto).toList();
    }

    @Transactional
    public UserDto createUser(UserRequest request) {
        return mapper.userDto(users.save(new UserEntity(request.name(), request.email())));
    }

    @Transactional
    public void deleteUser(long id) {
        users.delete(entities.user(id));
    }
}

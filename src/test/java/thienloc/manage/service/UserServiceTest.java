package thienloc.manage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import thienloc.manage.entity.User;
import thienloc.manage.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionService permissionService;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private UserService userService;

    @Test
    void testRegisterUser_EncodesPassword() {
        User user = User.builder().username("test").password("raw123").role("ROLE_USER").build();
        when(passwordEncoder.encode("raw123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.registerUser(user);

        assertEquals("$2a$encoded", result.getPassword());
        verify(passwordEncoder).encode("raw123");
    }

    @Test
    void testRegisterUser_NullRole_DefaultsToRoleUser() {
        User user = User.builder().username("test").password("pass").role(null).build();
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.registerUser(user);

        assertEquals("ROLE_USER", result.getRole());
    }

    @Test
    void testRegisterUser_EmptyRole_DefaultsToRoleUser() {
        User user = User.builder().username("test").password("pass").role("").build();
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.registerUser(user);

        assertEquals("ROLE_USER", result.getRole());
    }

    @Test
    void testRegisterUser_ExplicitRole_Preserved() {
        User user = User.builder().username("test").password("pass").role("ROLE_MANAGER").build();
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.registerUser(user);

        assertEquals("ROLE_MANAGER", result.getRole());
    }

    @Test
    void testFindByUsername_Found() {
        User user = User.builder().id(1L).username("admin").build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        User result = userService.findByUsername("admin");

        assertNotNull(result);
        assertEquals("admin", result.getUsername());
    }

    @Test
    void testFindByUsername_NotFound_ReturnsNull() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        User result = userService.findByUsername("unknown");

        assertNull(result);
    }

    @Test
    void testFindAllUsers() {
        List<User> users = List.of(
                User.builder().username("a").build(),
                User.builder().username("b").build());
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.findAllUsers();

        assertEquals(2, result.size());
    }

    @Test
    void testUpdateRole_Found() {
        User user = User.builder().id(1L).username("test").role("ROLE_USER").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateRole(1L, "ROLE_ADMIN");

        assertEquals("ROLE_ADMIN", result.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void testUpdateRole_NotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.updateRole(99L, "ROLE_ADMIN"));
    }
}

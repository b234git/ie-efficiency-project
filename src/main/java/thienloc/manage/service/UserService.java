package thienloc.manage.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import thienloc.manage.entity.User;
import thienloc.manage.repository.UserRepository;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User registerUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // Default role is USER unless otherwise specified
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("ROLE_USER");
        }
        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public User updateRole(Long id, String newRole) {
        User user = userRepository.findById(id).orElseThrow(() -> new thienloc.manage.exception.ResourceNotFoundException("User not found"));
        user.setRole(newRole);
        return userRepository.save(user);
    }

    public boolean changePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new thienloc.manage.exception.ResourceNotFoundException("User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return true;
    }
}

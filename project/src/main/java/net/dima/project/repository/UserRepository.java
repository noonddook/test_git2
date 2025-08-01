package net.dima.project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import net.dima.project.entity.UserEntity;

/**
 * 'users' 테이블에 대한 데이터베이스 작업을 처리하는 Spring Data JPA 리포지토리
 */
public interface UserRepository extends JpaRepository<UserEntity, Integer> {
    
    // 아이디로 사용자 정보를 조회 (중복 확인 및 로그인 시 사용)
    UserEntity findByUserId(String userId);

    // 이메일로 사용자 정보를 조회 (중복 확인 시 사용)
    UserEntity findByEmail(String email);

    // 소셜 로그인 제공자와 고유 ID로 사용자 정보를 조회 (카카오 로그인 시 사용)
    UserEntity findByProviderAndProviderId(String provider, String providerId);
}
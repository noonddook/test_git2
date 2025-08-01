package net.dima.project.repository;

import net.dima.project.entity.ContainerEntity;
import net.dima.project.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort; 

import java.util.List;

@Repository
public interface ContainerRepository extends JpaRepository<ContainerEntity, String> {
	// [✅ 기존 List<ContainerEntity> findByForwarder(UserEntity forwarder)를 아래 코드로 교체]
    List<ContainerEntity> findByForwarder(UserEntity forwarder, Sort sort);
}
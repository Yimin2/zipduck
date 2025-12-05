package com.zipduck.domain.auth;

import org.springframework.data.repository.CrudRepository;

/**
 * LinkToken Redis Repository
 */
public interface LinkTokenRepository extends CrudRepository<LinkToken, String> {
}

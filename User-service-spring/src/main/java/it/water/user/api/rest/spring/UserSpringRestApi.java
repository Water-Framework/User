/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.water.user.api.rest.spring;

import it.water.core.api.model.PaginableResult;
import it.water.core.api.service.rest.FrameworkRestApi;
import it.water.user.api.rest.UserRestApi;
import it.water.user.model.WaterUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * @Author Aristide Cittadino
 * Interface exposing same methods of its parent UserRestApi but adding Spring annotations.
 * Swagger annotation should be found because they have been defined in the parent UserRestApi.
 */
@RequestMapping("/users")
@FrameworkRestApi
public interface UserSpringRestApi extends UserRestApi {
    @PostMapping
    WaterUser save(@RequestBody  WaterUser user);

    @PutMapping
    WaterUser update(@RequestBody WaterUser user);

    @GetMapping("/{id}")
    WaterUser find(@PathVariable("id") long id);

    @GetMapping
    PaginableResult<WaterUser> findAll();

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable("id") long id);

    @PostMapping("/register")
    WaterUser register(@RequestBody WaterUser user);

    @PutMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    void activate(@RequestParam String email, @RequestParam String activationCode);

    @PutMapping("/{userId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    void activate(@PathVariable long userId);

    @PutMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    void deactivate(@PathVariable("id") long userId);

    @DeleteMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    void unregister(@RequestParam String email, @RequestParam String deletionCode);

    @PutMapping("/resetPasswordRequest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    void passwordResetRequest(@RequestParam String email);
}

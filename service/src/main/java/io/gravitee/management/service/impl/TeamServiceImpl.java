/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.service.impl;

import io.gravitee.management.model.NewTeamEntity;
import io.gravitee.management.model.TeamEntity;
import io.gravitee.management.model.UpdateTeamEntity;
import io.gravitee.management.service.TeamService;
import io.gravitee.management.service.exceptions.TeamAlreadyExistsException;
import io.gravitee.management.service.exceptions.TeamNotFoundException;
import io.gravitee.management.service.exceptions.TechnicalManagementException;
import io.gravitee.repository.api.TeamMembershipRepository;
import io.gravitee.repository.api.TeamRepository;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.model.Member;
import io.gravitee.repository.model.Team;
import io.gravitee.repository.model.TeamRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 */
@Component
public class TeamServiceImpl implements TeamService {

    /**
     * Logger.
     */
    private final Logger LOGGER = LoggerFactory.getLogger(TeamServiceImpl.class);

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMembershipRepository teamMembershipRepository;

    @Override
    public Optional<TeamEntity> findByName(String teamName) {
        try {
            LOGGER.debug("Find team by name: {}", teamName);
            return teamRepository.findByName(teamName).map(TeamServiceImpl::convert);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find a team using its name {}", teamName, ex);
            throw new TechnicalManagementException("An error occurs while trying to find a team using its name " + teamName, ex);
        }
    }

    @Override
        public TeamEntity create(NewTeamEntity newTeamEntity, String owner) {
        try {
            LOGGER.debug("Create {} by user {}", newTeamEntity, owner);

            Optional<TeamEntity> checkTeam = findByName(newTeamEntity.getName());
            if (checkTeam.isPresent()) {
                throw new TeamAlreadyExistsException(newTeamEntity.getName());
            }

            Team team = convert(newTeamEntity);

            // Set date fields
            team.setCreatedAt(new Date());
            team.setUpdatedAt(team.getCreatedAt());

            // Private by default
            team.setPrivate(true);

            // Create the team
            Team createdTeam = teamRepository.create(team);

            // Create default admin member
            Member ownerMember = new Member();
            ownerMember.setCreatedAt(new Date());
            ownerMember.setUpdatedAt(ownerMember.getCreatedAt());
            ownerMember.setUsername(owner);
            ownerMember.setRole(TeamRole.ADMIN);

            teamMembershipRepository.addMember(createdTeam.getName(), ownerMember);

            return convert(createdTeam);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create {} by user {}", newTeamEntity, owner, ex);
            throw new TechnicalManagementException("An error occurs while trying create " + newTeamEntity + " by user " + owner, ex);
        }
    }

    @Override
    public TeamEntity update(String teamName, UpdateTeamEntity updateTeamEntity) {
        try {
            LOGGER.debug("Update Team {}", teamName);

            Optional<Team> optTeamToUpdate = teamRepository.findByName(teamName);
            if (! optTeamToUpdate.isPresent()) {
                throw new TeamNotFoundException(teamName);
            }

            Team teamToUpdate = optTeamToUpdate.get();
            Team team = convert(updateTeamEntity);

            team.setName(teamName);
            team.setUpdatedAt(new Date());
            team.setPrivate(updateTeamEntity.isPrivate());

            // Copy fields from existing values
            team.setCreatedAt(teamToUpdate.getCreatedAt());

            Team updatedTeam =  teamRepository.update(team);
            return convert(updatedTeam);

        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to update team {}", teamName, ex);
            throw new TechnicalManagementException("An error occurs while trying to update team " + teamName, ex);
        }
    }

    @Override
    public Set<TeamEntity> findByUser(String username, boolean publicOnly) {
        try {
            LOGGER.debug("Find teams for user {}", username);
            Set<Team> teams = teamMembershipRepository.findByUser(username);
            Set<TeamEntity> teamEntities = new HashSet<>();

            if (publicOnly) {
                teamEntities.addAll(teams.stream().filter(new Predicate<Team>() {
                    @Override
                    public boolean test(Team team) {
                        return !team.isPrivate();
                    }
                }).map(TeamServiceImpl::convert).collect(Collectors.toSet()));
            } else {
                teamEntities.addAll(teams.stream().map(TeamServiceImpl::convert).collect(Collectors.toSet()));
            }

            return teamEntities;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find teams for user {}", username, ex);
            throw new TechnicalManagementException("An error occurs while trying to find teams for user " + username, ex);
        }
    }

    @Override
    public Set<TeamEntity> findAll(boolean publicOnly) {
        try {
            LOGGER.debug("Find all teams");
            Set<Team> teams = teamRepository.findAll(publicOnly);
            Set<TeamEntity> publicTeams = new HashSet<>(teams.size());

            for(Team team : teams) {
                publicTeams.add(convert(team));
            }

            return publicTeams;
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find all teams", ex);
            throw new TechnicalManagementException("An error occurs while trying to find all teams", ex);
        }
    }

    private static TeamEntity convert(Team team) {
        TeamEntity teamEntity = new TeamEntity();

        teamEntity.setName(team.getName());
        teamEntity.setIsPrivate(team.isPrivate());
        teamEntity.setDescription(team.getDescription());
        teamEntity.setEmail(team.getEmail());

        teamEntity.setUpdatedAt(team.getUpdatedAt());
        teamEntity.setCreatedAt(team.getCreatedAt());

        return teamEntity;
    }

    private static Team convert(NewTeamEntity newTeamEntity) {
        Team team = new Team();

        team.setName(newTeamEntity.getName());
        team.setDescription(newTeamEntity.getDescription());
        team.setEmail(newTeamEntity.getEmail());

        return team;
    }

    private static Team convert(UpdateTeamEntity updateTeamEntity) {
        Team team = new Team();

        team.setDescription(updateTeamEntity.getDescription());
        team.setEmail(updateTeamEntity.getEmail());
        team.setPrivate(updateTeamEntity.isPrivate());

        return team;
    }
}
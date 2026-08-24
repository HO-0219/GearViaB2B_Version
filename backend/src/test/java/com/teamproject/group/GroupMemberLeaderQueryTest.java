package com.teamproject.group;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the broadcast-recipient query the admin notice feature depends on. */
@DataJpaTest
class GroupMemberLeaderQueryTest {
    @Autowired private UserRepository users;
    @Autowired private GroupRepository groups;
    @Autowired private GroupMemberRepository members;

    private User user(String username) {
        return users.save(new User(username, username + "@example.com", "hash", username, true));
    }

    @Test
    void findsOnlyActiveLeadersOfTeamGroupsExactlyOncePerLeader() {
        User teamLeaderOfTwoTeams = user("dual-leader");
        User teamMember = user("member");
        User personalOwner = user("personal-owner");
        User removedLeader = user("removed-leader");
        User suspendedLeader = user("suspended-leader");
        suspendedLeader.suspend();
        users.save(suspendedLeader);

        Group teamOne = groups.save(Group.team("Team One", null, "Asia/Seoul", teamLeaderOfTwoTeams));
        Group teamTwo = groups.save(Group.team("Team Two", null, "Asia/Seoul", teamLeaderOfTwoTeams));
        Group personal = groups.save(Group.personal(personalOwner));

        members.save(GroupMember.leader(teamOne, teamLeaderOfTwoTeams));
        members.save(GroupMember.leader(teamTwo, teamLeaderOfTwoTeams));
        members.save(GroupMember.member(teamOne, teamMember));
        members.save(GroupMember.leader(personal, personalOwner));
        GroupMember removed = members.save(GroupMember.leader(teamOne, removedLeader));
        removed.remove();
        members.save(GroupMember.leader(teamOne, suspendedLeader));

        var leaders = members.findDistinctActiveTeamLeaderUsers();

        assertThat(leaders).containsExactly(teamLeaderOfTwoTeams);
    }
}

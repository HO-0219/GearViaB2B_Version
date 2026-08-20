package com.teamproject.group.application;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.User;
import org.springframework.stereotype.Service;

@Service
public class PersonalGroupProvisioner {
    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public PersonalGroupProvisioner(GroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    public void createFor(User user) {
        if (groups.existsByTypeAndCreatedById(Group.Type.PERSONAL, user.getId())) {
            throw new IllegalStateException("개인 그룹은 사용자당 하나만 생성할 수 있습니다.");
        }
        Group group = groups.save(Group.personal(user));
        members.save(GroupMember.leader(group, user));
    }
}

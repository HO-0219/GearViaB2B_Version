package com.teamproject.group.presentation;

import com.teamproject.group.application.GroupInvitationService;
import com.teamproject.group.application.dto.GroupDtos.MemberResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/group-invitations")
public class GroupInvitationController {
    private final GroupInvitationService invitations;

    public GroupInvitationController(GroupInvitationService invitations) { this.invitations = invitations; }

    @PostMapping("/{token}/accept")
    MemberResponse accept(Authentication authentication, @PathVariable String token) {
        return invitations.accept((Long) authentication.getPrincipal(), token);
    }
}

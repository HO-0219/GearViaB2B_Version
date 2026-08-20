package com.teamproject.project.presentation;
import com.teamproject.project.application.EmergencyIssueService;
import com.teamproject.project.application.dto.EmergencyIssueDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
@RestController @RequestMapping("/api/v1")
public class EmergencyIssueController {
    private final EmergencyIssueService service;
    public EmergencyIssueController(EmergencyIssueService service){this.service=service;}
    @GetMapping("/groups/{groupId}/emergency-issues") List<Response> list(Authentication auth,@PathVariable Long groupId){return service.list((Long)auth.getPrincipal(),groupId);}
    @PostMapping("/groups/{groupId}/emergency-issues") @ResponseStatus(HttpStatus.CREATED) Response create(Authentication auth,@PathVariable Long groupId,@Valid @RequestBody CreateRequest request){return service.create((Long)auth.getPrincipal(),groupId,request);}
    @PostMapping("/emergency-issues/{issueId}/image") Response image(Authentication auth,@PathVariable Long issueId,@RequestPart("file") MultipartFile file){return service.attachImage((Long)auth.getPrincipal(),issueId,file);}
    @PatchMapping("/emergency-issues/{issueId}/status") Response status(Authentication auth,@PathVariable Long issueId,@Valid @RequestBody StatusRequest request){return service.changeStatus((Long)auth.getPrincipal(),issueId,request);}
}

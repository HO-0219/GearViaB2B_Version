import { request } from './client';
export type EmergencyIssue = {
  id:number; groupId:number; projectId:number; projectName:string; createdByMemberId:number;
  createdByNickname:string; title:string; description?:string; audience:'PROJECT_PARTICIPANTS'|'WHOLE_TEAM';
  status:'OPEN'|'RESOLVED'; imageUrl?:string; resolvedAt?:string; createdAt:string; updatedAt:string;
  version:number; canManage:boolean;
};
export const emergencyIssueApi = {
  list:(groupId:number)=>request<EmergencyIssue[]>(`/groups/${groupId}/emergency-issues`,{},true),
  create:(groupId:number,body:{projectId:number;title:string;description?:string;audience:EmergencyIssue['audience']})=>request<EmergencyIssue>(`/groups/${groupId}/emergency-issues`,{method:'POST',body:JSON.stringify(body)},true),
  uploadImage:(issueId:number,file:File)=>{const body=new FormData();body.append('file',file);return request<EmergencyIssue>(`/emergency-issues/${issueId}/image`,{method:'POST',body},true);},
  status:(issueId:number,status:EmergencyIssue['status'],expectedVersion:number)=>request<EmergencyIssue>(`/emergency-issues/${issueId}/status`,{method:'PATCH',body:JSON.stringify({status,expectedVersion})},true),
};

package com.example.ai;

import com.example.ai.support.IntegrationTest;
import com.example.ai.support.TestUsers;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class RoomAccessIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String createRoom(TestUsers.Registered owner, String body) throws Exception {
        String json = mvc.perform(post("/api/v1/rooms").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    @Test
    void nonMemberCannotReadMessagesOrMembersUntilJoining() throws Exception {
        var owner = TestUsers.register(mvc, "owner");
        var outsider = TestUsers.register(mvc, "outsider");
        String roomId = createRoom(owner, "{\"name\":\"secret plans\",\"isPrivate\":true}");

        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", outsider.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/rooms/{id}/members", roomId).header("Authorization", outsider.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", outsider.bearer()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", outsider.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanReadOwnRoom() throws Exception {
        var owner = TestUsers.register(mvc, "owner2");
        String roomId = createRoom(owner, "{\"name\":\"mine\"}");

        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void protectedRoomRequiresCorrectPassword() throws Exception {
        var owner = TestUsers.register(mvc, "pwowner");
        var guest = TestUsers.register(mvc, "pwguest");
        String roomId = createRoom(owner, "{\"name\":\"locked\",\"password\":\"open-sesame\"}");

        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", guest.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incorrect room password"));
        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", guest.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", guest.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"open-sesame\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void inviteLinkUsesConfiguredFrontendUrl() throws Exception {
        var owner = TestUsers.register(mvc, "inviter");
        String roomId = createRoom(owner, "{\"name\":\"party\"}");

        mvc.perform(get("/api/v1/rooms/{id}/invite", roomId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(startsWith("http://localhost:5173/invite/")))
                .andExpect(jsonPath("$.url").value("http://localhost:5173/invite/" + roomId));
    }

    // Membership can also be removed by the owner kicking a member.
    @Test
    void kickedMemberLosesAccessAndOnlyOwnerCanKick() throws Exception {
        var owner = TestUsers.register(mvc, "kowner");
        var member = TestUsers.register(mvc, "kmember");
        var other = TestUsers.register(mvc, "kother");
        String roomId = createRoom(owner, "{\"name\":\"club\"}");
        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", other.bearer()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/rooms/{id}/members/{uid}", roomId, member.userId())
                        .header("Authorization", other.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/rooms/{id}/members/{uid}", roomId, owner.userId())
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/rooms/{id}/members/{uid}", roomId, member.userId())
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/rooms/{id}/members", roomId).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].userId", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(member.userId()))));
    }

    @Test
    void memberCanJoinThenLeaveAndLosesAccess() throws Exception {
        var owner = TestUsers.register(mvc, "lowner");
        var member = TestUsers.register(mvc, "lmember");
        String roomId = createRoom(owner, "{\"name\":\"drop-in\"}");

        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/rooms/joined").header("Authorization", member.bearer()))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(roomId)));

        mvc.perform(post("/api/v1/rooms/{id}/leave", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/rooms/joined").header("Authorization", member.bearer()))
                .andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(roomId))));
        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/rooms/{id}/leave", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You are not a member of this room"));

        // Rejoining works after leaving.
        mvc.perform(post("/api/v1/rooms/{id}/join", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/rooms/{id}/messages", roomId).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCannotLeaveOwnRoom() throws Exception {
        var owner = TestUsers.register(mvc, "stayer");
        String roomId = createRoom(owner, "{\"name\":\"mine\"}");

        mvc.perform(post("/api/v1/rooms/{id}/leave", roomId).header("Authorization", owner.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Room owners can't leave their own room. Delete it instead."));
    }
}

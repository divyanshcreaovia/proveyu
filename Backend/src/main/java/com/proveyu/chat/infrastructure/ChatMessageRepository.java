package com.proveyu.chat.infrastructure;

import com.proveyu.chat.domain.ChatMessage;
import com.proveyu.chat.domain.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("SELECT m FROM ChatMessage m WHERE (m.senderId = :userA AND m.receiverId = :userB) " +
           "OR (m.senderId = :userB AND m.receiverId = :userA) ORDER BY m.createdAt ASC")
    Page<ChatMessage> findConversation(@Param("userA") UUID userA, @Param("userB") UUID userB, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.receiverId = :receiverId AND m.status != 'READ' ORDER BY m.createdAt DESC")
    List<ChatMessage> findUnreadMessages(@Param("receiverId") UUID receiverId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.receiverId = :receiverId AND m.status != 'READ'")
    long countUnreadMessages(@Param("receiverId") UUID receiverId);

    List<ChatMessage> findByReceiverIdAndStatusNot(UUID receiverId, MessageStatus status);
}

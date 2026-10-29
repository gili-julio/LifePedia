package com.lifepedia.api.account;

import com.lifepedia.api.account.AccountDtos.DeleteRequest;
import com.lifepedia.api.account.AccountDtos.DeletionPreview;
import com.lifepedia.api.account.AccountDtos.GroupImpact;
import com.lifepedia.api.auth.AuthService;
import com.lifepedia.api.finance.category.CategoryRepository;
import com.lifepedia.api.finance.group.GroupRecurringEntryRepository;
import com.lifepedia.api.finance.group.GroupTransactionRepository;
import com.lifepedia.api.finance.recurring.RecurringEntryRepository;
import com.lifepedia.api.finance.transaction.TransactionRepository;
import com.lifepedia.api.group.GroupMember;
import com.lifepedia.api.group.GroupMemberRepository;
import com.lifepedia.api.group.GroupService;
import com.lifepedia.api.group.GroupService.Departure;
import com.lifepedia.api.notification.NotificationService;
import com.lifepedia.api.notification.NotificationType;
import com.lifepedia.api.planning.PlanItemRepository;
import com.lifepedia.api.task.TaskRepository;
import com.lifepedia.api.user.User;
import com.lifepedia.api.user.UserRepository;
import com.lifepedia.api.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exclui a conta: sai de cada grupo como no "Sair do grupo" (leva os itens, passa o admin, apaga grupo vazio), avisa
 * quem ficou e apaga o usuário — o banco apaga o resto em cascata.
 */
@Service
public class AccountDeletionService {

    private final AuthService auth;
    private final UserService userService;
    private final UserRepository users;
    private final GroupService groups;
    private final GroupMemberRepository members;
    private final NotificationService notifications;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final RecurringEntryRepository recurring;
    private final GroupTransactionRepository groupTransactions;
    private final GroupRecurringEntryRepository groupRecurring;
    private final PlanItemRepository planItems;
    private final TaskRepository tasks;

    public AccountDeletionService(AuthService auth, UserService userService, UserRepository users, GroupService groups,
                                  GroupMemberRepository members, NotificationService notifications,
                                  CategoryRepository categories, TransactionRepository transactions,
                                  RecurringEntryRepository recurring, GroupTransactionRepository groupTransactions,
                                  GroupRecurringEntryRepository groupRecurring, PlanItemRepository planItems,
                                  TaskRepository tasks) {
        this.auth = auth;
        this.userService = userService;
        this.users = users;
        this.groups = groups;
        this.members = members;
        this.notifications = notifications;
        this.categories = categories;
        this.transactions = transactions;
        this.recurring = recurring;
        this.groupTransactions = groupTransactions;
        this.groupRecurring = groupRecurring;
        this.planItems = planItems;
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public DeletionPreview preview(UUID userId) {
        List<GroupImpact> impacts = groups.departurePreview(userId).stream()
                .map(g -> new GroupImpact(g.groupId(), g.name(), g.otherMembers(), g.deletesGroup(), g.newAdmin(),
                        groupTransactions.findByGroupIdAndUserId(g.groupId(), userId).size(),
                        groupRecurring.findByGroupIdAndUserId(g.groupId(), userId).size(),
                        planItems.findByGroupIdAndUserId(g.groupId(), userId).size()))
                .toList();
        return new DeletionPreview(categories.findByUserId(userId).size(), transactions.countByUserId(userId),
                recurring.countByUserId(userId), tasks.countByUserId(userId), impacts);
    }

    @Transactional
    public void delete(UUID userId, DeleteRequest request) {
        auth.confirmIdentity(userId, request.password(), request.googleCredential());
        User user = userService.find(userId);
        for (GroupMember membership : members.findByUserId(userId)) {
            Departure departure = groups.leave(userId, membership.getGroupId());
            if (departure.groupDeleted()) continue;
            for (UUID other : departure.remaining()) {
                notifications.notify(other, NotificationType.MEMBER_ACCOUNT_DELETED, null, departure.groupId(), Map.of(
                        "actorName", user.getName(),
                        "actorUsername", user.getUsername() == null ? "" : user.getUsername(),
                        "groupName", departure.groupName(),
                        "promoted", Boolean.toString(other.equals(departure.newAdminId()))));
            }
        }
        userService.deleteImageAfterCommit(user);
        users.delete(user);
        users.flush();
    }
}

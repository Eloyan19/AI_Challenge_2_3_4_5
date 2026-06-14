package com.example.petapp.domain.model

/**
 * Domain representation of a conversation branch in the branching strategy.
 *
 * Branches form a tree rooted at the main branch (id = 1). When a user forks the conversation
 * at a checkpoint, a new [Branch] is created pointing back to its parent.
 * History reconstruction walks up the tree collecting ancestor messages up to
 * [checkpointMessageId] at each level, then appending the branch's own messages.
 *
 * @property id Auto-generated primary key.
 * @property name User-visible label for the branch.
 * @property parentBranchId Id of the parent branch, or null for the root branch.
 * @property checkpointMessageId Last message id inherited from the parent branch (inclusive).
 *   Messages in the parent with id > this value are not visible in this branch.
 *   Null means the branch inherits the entire parent history up to the fork point.
 * @property createdAt Unix epoch milliseconds when the branch was created.
 */
data class Branch(
    val id: Long,
    val name: String,
    val parentBranchId: Long?,
    val checkpointMessageId: Long?,
    val createdAt: Long
)

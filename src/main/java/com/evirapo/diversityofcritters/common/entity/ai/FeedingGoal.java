package com.evirapo.diversityofcritters.common.entity.ai;

/**
 * Marks a goal that walks to food or water, or eats or drinks. While one of these runs,
 * {@link SeekComfortGoal} does not interrupt it: an animal whose only food lies in a dangerous spot
 * would otherwise walk out as soon as it got there, and never eat.
 */
public interface FeedingGoal {
}

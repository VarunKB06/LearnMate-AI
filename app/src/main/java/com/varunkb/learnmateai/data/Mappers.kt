package com.varunkb.learnmateai.data

import com.varunkb.learnmateai.domain.LessonProgress
import com.varunkb.learnmateai.domain.LessonStatus
import com.varunkb.learnmateai.domain.Question
import com.varunkb.learnmateai.domain.Subject
import com.varunkb.learnmateai.domain.TopicMastery

private const val SEP = '\u001F'

fun Question.toEntity() = QuestionEntity(
    id = id, subjectId = subjectId, topic = topic, difficulty = difficulty, prompt = prompt,
    choices = choices.joinToString(SEP.toString()), answerIndex = answerIndex, explanation = explanation
)

fun QuestionEntity.toDomain() = Question(
    id = id, subjectId = subjectId, topic = topic, difficulty = difficulty, prompt = prompt,
    choices = choices.split(SEP), answerIndex = answerIndex, explanation = explanation
)

fun Subject.toEntity() = SubjectEntity(id, title, description)
fun SubjectEntity.toDomain() = Subject(id, title, description)

fun MasteryEntity.toDomain() = TopicMastery(subjectId, topic, pKnown, correct, total)
fun TopicMastery.toEntity(now: Long) = MasteryEntity(subjectId, topic, pKnown, correct, total, now)

fun LessonProgressEntity.toDomain() = LessonProgress(
    lessonId = lessonId, subjectId = subjectId,
    status = when (status) {
        "completed" -> LessonStatus.Completed
        "in_progress" -> LessonStatus.InProgress
        else -> LessonStatus.NotStarted
    },
    bookmarked = bookmarked, lastAccessed = lastAccessed, completedAt = completedAt
)

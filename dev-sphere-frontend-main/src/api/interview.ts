import request from '@/utils/request'

export interface Interview {
    id: string
    userId: string
    jobId: string
    startTime: string
    endTime: string
    status: number // 0: Ready, 1: In Progress, 2: Completed, 3: Aborted
    totalScore: number
    summary: string
}

export interface Question {
    id: string
    content: string
    category: string
    difficulty: number
    standardAnswer: string
}

export interface CreateInterviewReq {
    jobId?: string
    resumeUrl?: string
    category?: string
}

export const createInterview = (data: CreateInterviewReq) => {
    return request({
        url: '/interview/interviews',
        method: 'post',
        data
    })
}

export const startInterview = (id: string) => {
    return request({
        url: `/interview/interviews/${id}/start`,
        method: 'post'
    })
}

export const submitAnswer = (id: string, answer: string) => {
    return request({
        url: `/interview/interviews/${id}/submit`,
        method: 'post',
        data: { answer }
    })
}

export const completeInterview = (id: string) => {
    return request({
        url: `/interview/interviews/${id}/complete`,
        method: 'post'
    })
}

export const getInterview = (id: string) => {
    return request({
        url: `/interview/interviews/${id}`,
        method: 'get'
    })
}

export const uploadFile = (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('biz', 'resume')
    return request({
        url: '/oss/file/upload',
        method: 'post',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    })
}

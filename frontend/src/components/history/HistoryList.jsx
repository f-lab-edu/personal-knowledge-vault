/**
 * 과거 질문 목록. 날짜별 그룹핑
 */
import React from 'react';
import styles from './HistoryList.module.css';

// Mock History Data
const HISTORY_DATA = [
    {
        label: '오늘',
        items: [
            { id: 1, title: '팩토리 패턴 분석', time: '오전 10:23' },
            { id: 2, title: '배포 설정', time: '오전 09:45' },
        ]
    },
    {
        label: '어제',
        items: [
            { id: 3, title: '데이터베이스 스키마 검토', time: '오후 4:30' },
            { id: 4, title: '인증 플로우', time: '오후 2:15' },
            { id: 5, title: 'React Query 설정', time: '오전 11:00' },
        ]
    },
    {
        label: '지난 7일',
        items: [
            { id: 6, title: '프로젝트 킥오프 질문', time: '10월 20일' },
            { id: 7, title: '요구사항 수집', time: '10월 19일' },
        ]
    }
];

const HistoryGroup = ({ label, items }) => (
    <div className={styles.group}>
        <h3 className={styles.groupLabel}>
            {label}
        </h3>
        <div className={styles.list}>
            {items.map((item) => (
                <button
                    key={item.id}
                    className={styles.item}
                >
                    <div className={styles.itemTitle}>
                        {item.title}
                    </div>
                    <div className={styles.itemTime}>
                        {item.time}
                    </div>
                </button>
            ))}
        </div>
    </div>
);

const HistoryList = () => {
    return (
        <div className={styles.container}>
            {HISTORY_DATA.map((group, index) => (
                <HistoryGroup key={index} label={group.label} items={group.items} />
            ))}
        </div>
    );
};

export default HistoryList;

/**
 * AI 답변 말풍선. 출처 카드 포함
 */
import { clsx } from 'clsx';
import styles from './AnswerBubble.module.css';
import SourceCard from './SourceCard';

const AnswerBubble = ({ content, sources }) => {
    return (
        <div className={clsx(styles.container, 'animate-fade-in')}>
            {/* Answer Badge */}
            <div className={styles.badgeWrapper}>
                <div className={styles.badgeIcon}>
                    <svg width="10" height="10" fill="none" stroke="white" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M13 10V3L4 14h7v7l9-11h-7z" />
                    </svg>
                </div>
                <span className={styles.badgeLabel}>
                    AI 답변
                </span>
            </div>

            {/* Answer Content */}
            <div className={styles.contentWrapper}>
                <div className={styles.text}>
                    {content}
                </div>

                {/* Sources Section */}
                {sources && sources.length > 0 && (
                    <div className={styles.sourcesSection}>
                        <h4 className={styles.sourcesLabel}>
                            참고 문서
                        </h4>
                        <div className={styles.sourcesList}>
                            {sources.map((source, index) => (
                                <SourceCard key={index} source={source} />
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default AnswerBubble;

/**
 * 텍스트 위주의 미니멀 버튼 (Primary, Ghost 등)
 */
import React from 'react';
import { clsx } from 'clsx';
import styles from './Button.module.css';

const Button = ({
    children,
    variant = 'primary',
    size = 'md',
    isFullWidth = false,
    className,
    style,
    ...props
}) => {
    return (
        <button
            className={clsx(
                styles.button,
                styles[variant],
                styles[size],
                isFullWidth && styles.fullWidth,
                className
            )}
            style={style}
            {...props}
        >
            {children}
        </button>
    );
};

export default Button;

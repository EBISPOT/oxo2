import { ErrorProps } from "./ErrorSlice";
import { ExclamationTriangleIcon } from "@heroicons/react/24/solid";

export function ErrorInfo(error: ErrorProps) {
    return (
        <div className="alert-error">
            <ExclamationTriangleIcon className="w-6 h-6 mr-2 inline-block flex-shrink-0" />
            <span>An error "{error.message}" occurred while {error.task}.</span>
        </div>
    )
}